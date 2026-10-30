// Alibaba Cloud OSS regions, verified on 2026-10-06.
// https://help.aliyun.com/zh/oss/user-guide/regions-and-endpoints
// Standard regional endpoints only; exclude closing, finance, government,
// and regionless endpoints from new-space selection.
export const aliyunOssRegionGroups = [
  {
    label: '中国内地',
    regions: [
      { id: 'cn-hangzhou', label: '华东1（杭州）' },
      { id: 'cn-shanghai', label: '华东2（上海）' },
      { id: 'cn-wuhan-lr', label: '华中1（武汉-本地地域）' },
      { id: 'cn-qingdao', label: '华北1（青岛）' },
      { id: 'cn-beijing', label: '华北2（北京）' },
      { id: 'cn-zhangjiakou', label: '华北3（张家口）' },
      { id: 'cn-huhehaote', label: '华北5（呼和浩特）' },
      { id: 'cn-wulanchabu', label: '华北6（乌兰察布）' },
      { id: 'cn-shenzhen', label: '华南1（深圳）' },
      { id: 'cn-heyuan', label: '华南2（河源）' },
      { id: 'cn-guangzhou', label: '华南3（广州）' },
      { id: 'cn-chengdu', label: '西南1（成都）' },
      { id: 'cn-zhongwei', label: '西北2（中卫）' },
    ],
  },
  {
    label: '中国香港',
    regions: [{ id: 'cn-hongkong', label: '中国香港' }],
  },
  {
    label: '亚太',
    regions: [
      { id: 'ap-northeast-1', label: '日本（东京）' },
      { id: 'ap-northeast-2', label: '韩国（首尔）' },
      { id: 'ap-southeast-1', label: '新加坡' },
      { id: 'ap-southeast-3', label: '马来西亚（吉隆坡）' },
      { id: 'ap-southeast-5', label: '印度尼西亚（雅加达）' },
      { id: 'ap-southeast-6', label: '菲律宾（马尼拉）' },
      { id: 'ap-southeast-7', label: '泰国（曼谷）' },
      { id: 'ap-southeast-8', label: '马来西亚（柔佛州）' },
    ],
  },
  {
    label: '欧美',
    regions: [
      { id: 'sa-east-1', label: '巴西（圣保罗）' },
      { id: 'eu-central-1', label: '德国（法兰克福）' },
      { id: 'eu-west-1', label: '英国（伦敦）' },
      { id: 'us-west-1', label: '美国（硅谷）' },
      { id: 'us-east-1', label: '美国（弗吉尼亚）' },
      { id: 'na-south-1', label: '墨西哥' },
      { id: 'eu-west-2', label: '法国（巴黎）' },
    ],
  },
  {
    label: '中东',
    regions: [{ id: 'me-east-1', label: '阿联酋（迪拜）' }],
  },
]

export const aliyunOssRegionLabels = new Map<string, string>(
  aliyunOssRegionGroups.flatMap(({ regions }) => regions.map(({ id, label }) => [id, label] as const)),
)

export function getAliyunOssRegionId(regionId: string): string {
  return regionId.replace(/^oss-/, '')
}

export function getAliyunOssRegionLabel(regionId: string): string {
  return aliyunOssRegionLabels.get(getAliyunOssRegionId(regionId)) ?? regionId
}

export function getAliyunOssDefaultDomain(bucketName: string, regionId: string): string {
  return `https://${bucketName}.oss-${getAliyunOssRegionId(regionId)}.aliyuncs.com`
}
