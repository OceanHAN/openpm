<template>
  <el-drawer v-model="visible" :title="`团队：${title}`" size="820px" append-to-body>
    <div v-loading="loading">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="mb-15px"
        title="团队人数与可用工时都来自这张成员表"
        description="zt_team 一张表存项目与执行的成员。禅道的「团队人数」是从这里统计的，不是 zt_project.team 那个逗号串；可用工时 = 可用天数 × 每天投入小时数（默认 7 小时）。成员是物理增删：移除后可以重新加入。"
      />

      <div class="mb-10px">
        <el-button type="primary" plain @click="openAdd" v-hasPermi="['zentao:team:update']">
          <Icon icon="ep:plus" class="mr-5px" /> 添加成员
        </el-button>
        <span class="ml-10px text-gray-500">
          共 {{ members.length }} 人，可用工时合计 <strong>{{ totalHours }}</strong> 小时
        </span>
      </div>

      <el-table :data="members" border size="small" empty-text="还没有成员">
        <el-table-column label="账号" prop="account" width="110" />
        <el-table-column label="姓名" prop="realname" width="100" />
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-select v-model="row.role" size="small" filterable allow-create default-first-option
                       class="!w-110px" @change="saveMember(row)">
              <el-option v-for="r in TEAM_ROLE_OPTIONS" :key="r" :label="r" :value="r" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="加入日期" align="center" prop="join" width="110" />
        <el-table-column label="可用天数" align="center" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.days" size="small" :min="0" :controls="false"
                             class="!w-70px" @change="saveMember(row)" />
          </template>
        </el-table-column>
        <el-table-column label="每天小时" align="center" width="100">
          <template #default="{ row }">
            <el-input-number v-model="row.hours" size="small" :min="0" :precision="1" :step="0.5"
                             :controls="false" class="!w-70px" @change="saveMember(row)" />
          </template>
        </el-table-column>
        <el-table-column label="可用工时" align="center" width="90">
          <template #default="{ row }">{{ Number(row.days || 0) * Number(row.hours || 0) }}</template>
        </el-table-column>
        <el-table-column label="受限" align="center" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.limited" active-value="yes" inactive-value="no"
                       @change="saveMember(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="handleRemove(row)"
                       v-hasPermi="['zentao:team:update']">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="addVisible" title="添加成员" width="460px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="账号">
          <el-select v-model="addForm.account" filterable allow-create default-first-option
                     placeholder="选择或输入账号" class="!w-280px">
            <el-option v-for="u in userList" :key="u.account" :label="`${u.realname || u.account}（${u.account}）`"
                       :value="u.account!" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="addForm.role" filterable allow-create default-first-option class="!w-200px">
            <el-option v-for="r in TEAM_ROLE_OPTIONS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="可用天数">
          <el-input-number v-model="addForm.days" :min="0" class="!w-140px" />
        </el-form-item>
        <el-form-item label="每天小时">
          <el-input-number v-model="addForm.hours" :min="0" :precision="1" :step="0.5" class="!w-140px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleAdd">添加</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as TeamApi from '@/api/zentao/team'
import * as OrgApi from '@/api/zentao/organization'
import { TEAM_ROLE_OPTIONS } from '@/api/zentao/team'

defineOptions({ name: 'ZentaoTeamPanel' })

const emit = defineEmits(['success'])
const message = useMessage()

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const title = ref('')
const root = ref<number>()
const type = ref('project')
const members = ref<TeamApi.TeamMemberVO[]>([])
const totalHours = ref(0)
const userList = ref<OrgApi.OrgUserVO[]>([])

const addVisible = ref(false)
const addForm = reactive<TeamApi.TeamMemberVO>({
  account: '',
  role: '研发',
  days: 0,
  hours: 7
})

const load = async () => {
  if (!root.value) return
  loading.value = true
  try {
    members.value = await TeamApi.getTeamList(root.value, type.value)
    totalHours.value = Number(await TeamApi.getTeamTotalHours(root.value, type.value))
  } finally {
    loading.value = false
  }
}

/**
 * 打开某对象（项目/执行）的团队
 * @param row  {id, name} —— 项目或执行
 * @param kind 'project' | 'execution'
 */
const open = async (row: { id?: number; name?: string }, kind = 'project') => {
  root.value = row.id
  type.value = kind
  title.value = row.name || `#${row.id}`
  visible.value = true
  if (!userList.value.length) {
    try {
      userList.value = await OrgApi.getUserList({})
    } catch {
      userList.value = []
    }
  }
  await load()
}

const openAdd = () => {
  Object.assign(addForm, { account: '', role: '研发', days: 0, hours: 7 })
  addVisible.value = true
}

const handleAdd = async () => {
  if (!addForm.account) {
    message.warning('请选择或输入账号')
    return
  }
  saving.value = true
  try {
    await TeamApi.addTeamMember({ ...addForm, root: root.value, type: type.value })
    message.success('成员已添加')
    addVisible.value = false
    await load()
    emit('success')
  } finally {
    saving.value = false
  }
}

const saveMember = async (row: TeamApi.TeamMemberVO) => {
  await TeamApi.updateTeamMember({
    id: row.id,
    root: root.value,
    type: type.value,
    account: row.account,
    role: row.role,
    days: row.days,
    hours: row.hours,
    limited: row.limited
  })
  message.success('已保存')
  await load()
  emit('success')
}

const handleRemove = async (row: TeamApi.TeamMemberVO) => {
  await message.delConfirm(`确认把「${row.realname || row.account}」移出团队？`)
  await TeamApi.removeTeamMember(row.id!)
  message.success('已移除')
  await load()
  emit('success')
}

defineExpose({ open })
</script>
